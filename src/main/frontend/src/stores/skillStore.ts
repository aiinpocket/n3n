import { create } from 'zustand'
import { skillApi, type Skill, type CreateSkillRequest, type UpdateSkillRequest, type ExecuteSkillResponse } from '../api/skill'
import { logger } from '../utils/logger'

interface SkillState {
  skills: Skill[]
  builtinSkills: Skill[]
  categories: string[]
  selectedSkill: Skill | null
  isLoading: boolean
  error: string | null

  // Actions
  fetchSkills: () => Promise<void>
  fetchBuiltinSkills: () => Promise<void>
  fetchCategories: () => Promise<void>
  fetchSkillsByCategory: (category: string) => Promise<Skill[]>
  getSkill: (id: string) => Promise<Skill>
  createSkill: (request: CreateSkillRequest) => Promise<Skill>
  updateSkill: (id: string, request: UpdateSkillRequest) => Promise<Skill>
  deleteSkill: (id: string) => Promise<void>
  executeSkill: (id: string, input: Record<string, unknown>) => Promise<ExecuteSkillResponse>
  setSelectedSkill: (skill: Skill | null) => void
  clearError: () => void
}

export const useSkillStore = create<SkillState>((set, get) => ({
  skills: [],
  builtinSkills: [],
  categories: [],
  selectedSkill: null,
  isLoading: false,
  error: null,

  fetchSkills: async () => {
    set({ isLoading: true, error: null })
    try {
      const skills = await skillApi.list()
      set({ skills, isLoading: false })
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false })
    }
  },

  fetchBuiltinSkills: async () => {
    set({ isLoading: true, error: null })
    try {
      const builtinSkills = await skillApi.listBuiltin()
      set({ builtinSkills, isLoading: false })
    } catch (error) {
      set({ error: (error as Error).message, isLoading: false })
    }
  },

  fetchCategories: async () => {
    try {
      const categories = await skillApi.listCategories()
      set({ categories })
    } catch (error) {
      logger.error('Failed to fetch categories:', error)
    }
  },

  fetchSkillsByCategory: async (category: string) => {
    try {
      return await skillApi.listByCategory(category)
    } catch (error) {
      logger.error('Failed to fetch skills by category:', error)
      return []
    }
  },

  getSkill: async (id: string) => {
    const skill = await skillApi.get(id)
    set({ selectedSkill: skill })
    return skill
  },

  createSkill: async (request: CreateSkillRequest) => {
    const skill = await skillApi.create(request)
    const { skills } = get()
    set({ skills: [...skills, skill] })
    return skill
  },

  updateSkill: async (id: string, request: UpdateSkillRequest) => {
    const skill = await skillApi.update(id, request)
    const { skills } = get()
    set({
      skills: skills.map((s) => (s.id === id ? skill : s)),
      selectedSkill: skill
    })
    return skill
  },

  deleteSkill: async (id: string) => {
    await skillApi.delete(id)
    const { skills } = get()
    set({ skills: skills.filter((s) => s.id !== id) })
  },

  executeSkill: async (id: string, input: Record<string, unknown>) => {
    return await skillApi.execute(id, input)
  },

  setSelectedSkill: (skill: Skill | null) => {
    set({ selectedSkill: skill })
  },

  clearError: () => {
    set({ error: null })
  }
}))

export default useSkillStore
