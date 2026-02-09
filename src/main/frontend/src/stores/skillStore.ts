import { create } from 'zustand'
import { skillApi, type Skill, type CreateSkillRequest, type UpdateSkillRequest, type ExecuteSkillResponse } from '../api/skill'
import { extractApiError } from '../utils/errorMessages'
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
      set({ error: extractApiError(error), isLoading: false })
    }
  },

  fetchBuiltinSkills: async () => {
    set({ isLoading: true, error: null })
    try {
      const builtinSkills = await skillApi.listBuiltin()
      set({ builtinSkills, isLoading: false })
    } catch (error) {
      set({ error: extractApiError(error), isLoading: false })
    }
  },

  fetchCategories: async () => {
    try {
      const categories = await skillApi.listCategories()
      set({ categories })
    } catch (error) {
      logger.error('Failed to fetch categories:', error)
      set({ error: extractApiError(error) })
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
    set({ error: null })
    try {
      const skill = await skillApi.get(id)
      set({ selectedSkill: skill })
      return skill
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  createSkill: async (request: CreateSkillRequest) => {
    set({ error: null })
    try {
      const skill = await skillApi.create(request)
      const { skills } = get()
      set({ skills: [...skills, skill] })
      return skill
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  updateSkill: async (id: string, request: UpdateSkillRequest) => {
    set({ error: null })
    try {
      const skill = await skillApi.update(id, request)
      const { skills } = get()
      set({
        skills: skills.map((s) => (s.id === id ? skill : s)),
        selectedSkill: skill
      })
      return skill
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  deleteSkill: async (id: string) => {
    set({ error: null })
    try {
      await skillApi.delete(id)
      const { skills } = get()
      set({ skills: skills.filter((s) => s.id !== id) })
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  executeSkill: async (id: string, input: Record<string, unknown>) => {
    set({ error: null })
    try {
      return await skillApi.execute(id, input)
    } catch (error) {
      set({ error: extractApiError(error) })
      throw error
    }
  },

  setSelectedSkill: (skill: Skill | null) => {
    set({ selectedSkill: skill })
  },

  clearError: () => {
    set({ error: null })
  }
}))

export default useSkillStore
