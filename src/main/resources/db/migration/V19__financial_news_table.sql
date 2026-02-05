-- V19: Financial News Table for Web Scraper Flow
-- This table stores financial news articles collected by the scraper flow template

CREATE TABLE IF NOT EXISTS financial_news (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(500) NOT NULL,
    url VARCHAR(2000) NOT NULL UNIQUE,
    source VARCHAR(200),
    content TEXT,
    summary TEXT,
    published_at TIMESTAMP WITH TIME ZONE,
    scraped_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sentiment VARCHAR(20), -- positive, negative, neutral
    tags TEXT[], -- Array of tags/categories
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_financial_news_scraped_at ON financial_news(scraped_at DESC);
CREATE INDEX IF NOT EXISTS idx_financial_news_published_at ON financial_news(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_financial_news_source ON financial_news(source);
CREATE INDEX IF NOT EXISTS idx_financial_news_sentiment ON financial_news(sentiment);
CREATE INDEX IF NOT EXISTS idx_financial_news_tags ON financial_news USING GIN(tags);

-- Price monitoring table for product price scraper
CREATE TABLE IF NOT EXISTS price_monitoring (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name VARCHAR(500) NOT NULL,
    product_url VARCHAR(2000) NOT NULL,
    current_price DECIMAL(15, 2),
    previous_price DECIMAL(15, 2),
    lowest_price DECIMAL(15, 2),
    highest_price DECIMAL(15, 2),
    currency VARCHAR(10) DEFAULT 'TWD',
    target_price DECIMAL(15, 2),
    is_active BOOLEAN DEFAULT true,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    price_history JSONB DEFAULT '[]',
    user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_price_monitoring_user ON price_monitoring(user_id);
CREATE INDEX IF NOT EXISTS idx_price_monitoring_active ON price_monitoring(is_active);

-- Social mentions table for brand monitoring scraper
CREATE TABLE IF NOT EXISTS social_mentions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform VARCHAR(100) NOT NULL, -- twitter, facebook, reddit, etc.
    content TEXT NOT NULL,
    author VARCHAR(500),
    author_url VARCHAR(2000),
    post_url VARCHAR(2000) UNIQUE,
    sentiment VARCHAR(20), -- positive, negative, neutral
    sentiment_score DECIMAL(5, 4), -- -1.0 to 1.0
    brand_keyword VARCHAR(200),
    engagement_count INTEGER DEFAULT 0,
    posted_at TIMESTAMP WITH TIME ZONE,
    scraped_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_social_mentions_platform ON social_mentions(platform);
CREATE INDEX IF NOT EXISTS idx_social_mentions_sentiment ON social_mentions(sentiment);
CREATE INDEX IF NOT EXISTS idx_social_mentions_brand ON social_mentions(brand_keyword);
CREATE INDEX IF NOT EXISTS idx_social_mentions_scraped ON social_mentions(scraped_at DESC);

COMMENT ON TABLE financial_news IS 'Stores financial news articles collected by web scraper flows';
COMMENT ON TABLE price_monitoring IS 'Tracks product prices for price monitoring scraper flows';
COMMENT ON TABLE social_mentions IS 'Stores social media mentions for brand monitoring flows';
